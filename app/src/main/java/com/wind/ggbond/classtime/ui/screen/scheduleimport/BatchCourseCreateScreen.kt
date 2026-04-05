@file:OptIn(ExperimentalLayoutApi::class)

package com.wind.ggbond.classtime.ui.screen.scheduleimport

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.wind.ggbond.classtime.ui.navigation.BottomNavItem
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.screen.course.components.WeekSelectorDialog
import com.wind.ggbond.classtime.ui.components.ScheduleSelectionState
import com.wind.ggbond.classtime.ui.components.ScheduleExpiredDialog
import com.wind.ggbond.classtime.ui.components.CreateScheduleDialog
import com.wind.ggbond.classtime.util.DateUtils
import com.wind.ggbond.classtime.util.WeekParser

enum class BatchPhase {
    BASIC_INFO,
    DETAILED_CONFIG,
    COLOR_SELECTION,
    PREVIEW
}

enum class ConfigStep(val step: Int) {
    TIME_SCHEDULE(1),
    WEEK_SELECTION(2),
    REMINDER_AND_NOTES(3)
}

val BATCH_COLOR_OPTIONS = listOf(
    Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6),
    Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFF4DD0E1),
    Color(0xFFFF8A65), Color(0xFFA1887F), Color(0xFF7986CB),
    Color(0xFF4DB6AC), Color(0xFFF06292), Color(0xFF9575CD)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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

    var currentPhase by remember { mutableStateOf(BatchPhase.BASIC_INFO) }
    var currentCourseIndex by remember { mutableIntStateOf(0) }
    var currentConfigStep by remember { mutableIntStateOf(1) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            snackbarMessage = null
        }
    }

    when (val state = scheduleState) {
        is ScheduleSelectionState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        is ScheduleSelectionState.NeedCreate -> {
            CreateScheduleDialog(
                onConfirm = { name, startDate, totalWeeks -> viewModel.createSchedule(name, startDate, totalWeeks) },
                onDismiss = { navController.navigateUp() }
            )
            return
        }
        is ScheduleSelectionState.Expired -> {
            ScheduleExpiredDialog(
                schedule = state.schedule,
                onContinue = { viewModel.confirmUseExpiredSchedule() },
                onCreateNew = { viewModel.switchToCreateNewSchedule() },
                onDismiss = { navController.navigateUp() }
            )
            return
        }
        is ScheduleSelectionState.Ready -> {}
    }

    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is BatchSaveState.Success -> {
                snackbarHostState.showSnackbar(
                    "成功创建 ${state.courseCount} 门课程（${state.recordCount} 条记录）",
                    duration = SnackbarDuration.Short
                )
                kotlinx.coroutines.delay(800)
                // 直接导航到课表标签页，不使用 Screen.Main.createRoute
                navController.navigate(BottomNavItem.Schedule.route) {
                    // 清空到根路由
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = false
                    }
                    // 避免重复导航
                    launchSingleTop = true
                    // 恢复状态
                    restoreState = true
                }
            }
            is BatchSaveState.Error -> {
                snackbarHostState.showSnackbar(state.message, duration = SnackbarDuration.Long)
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        when (currentPhase) {
                            BatchPhase.BASIC_INFO -> "批量添加课程 - 基础信息"
                            BatchPhase.DETAILED_CONFIG -> if (courseItems.isNotEmpty())
                                "配置 ${courseItems[currentCourseIndex].courseName}"
                                else "课程详情"
                            BatchPhase.COLOR_SELECTION -> "选择课程颜色"
                            BatchPhase.PREVIEW -> "预览确认"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (currentPhase) {
                            BatchPhase.DETAILED_CONFIG -> currentPhase = BatchPhase.BASIC_INFO
                            BatchPhase.COLOR_SELECTION -> currentPhase = BatchPhase.DETAILED_CONFIG
                            BatchPhase.PREVIEW -> currentPhase = BatchPhase.COLOR_SELECTION
                            else -> navController.navigateUp()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            when (currentPhase) {
                                BatchPhase.DETAILED_CONFIG -> "返回基础信息"
                                BatchPhase.COLOR_SELECTION -> "返回配置"
                                BatchPhase.PREVIEW -> "返回颜色选择"
                                else -> "返回"
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (currentPhase) {
                BatchPhase.BASIC_INFO -> BasicInfoPhase(
                    courseItems = courseItems,
                    viewModel = viewModel,
                    onNext = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (courseItems.isNotEmpty()) {
                            val hasEmptyName = courseItems.any { it.courseName.isBlank() }
                            if (hasEmptyName) {
                                snackbarMessage = "请填写所有课程名称"
                                return@BasicInfoPhase
                            }
                            currentCourseIndex = 0
                            currentConfigStep = 1
                            currentPhase = BatchPhase.DETAILED_CONFIG
                        }
                    }
                )

                BatchPhase.DETAILED_CONFIG -> DetailedConfigPhase(
                    courseItems = courseItems,
                    currentIndex = currentCourseIndex,
                    currentStep = currentConfigStep,
                    viewModel = viewModel,
                    onPreviousCourse = { if (currentCourseIndex > 0) currentCourseIndex-- },
                    onNextCourse = {
                        if (currentCourseIndex < courseItems.size - 1) {
                            currentCourseIndex++
                            currentConfigStep = 1
                        }
                    },
                    onStepChange = { currentConfigStep = it },
                    onComplete = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentPhase = BatchPhase.COLOR_SELECTION
                    }
                )

                BatchPhase.COLOR_SELECTION -> ColorSelectionPhase(
                    courseItems = courseItems,
                    viewModel = viewModel,
                    onBack = { currentPhase = BatchPhase.DETAILED_CONFIG },
                    onNext = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentPhase = BatchPhase.PREVIEW
                    }
                )

                BatchPhase.PREVIEW -> PreviewPhase(
                    courseItems = courseItems,
                    onEdit = { index ->
                        currentCourseIndex = index
                        currentConfigStep = 1
                        currentPhase = BatchPhase.DETAILED_CONFIG
                    },
                    onSaveAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.saveAll()
                    },
                    onBack = { currentPhase = BatchPhase.COLOR_SELECTION },
                    viewModel = viewModel
                )
            }
        }
    }

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

    showClipboardImport?.let { importTarget ->
        ClipboardImportDialog(
            onDismiss = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.hideClipboardImport()
            },
            onConfirm = { text ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.applyFromClipboard(importTarget, text)
                viewModel.hideClipboardImport()
            },
            clipboardManager = LocalClipboardManager.current
        )
    }
}

@Composable
private fun ClipboardImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        clipboardManager.getText()?.let { text = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从剪贴板导入") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("粘贴课程信息") },
                placeholder = { Text("在此粘贴从其他应用复制的课程信息...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicInfoPhase(
    courseItems: List<BatchCourseItem>,
    viewModel: BatchCourseCreateViewModel,
    onNext: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val expandedCardIds = remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(courseItems.size) {
        if (courseItems.isNotEmpty()) {
            val lastId = courseItems.last().id
            expandedCardIds.value = setOf(lastId)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.List, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = "已添加 ${courseItems.size} 门课程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }

        if (courseItems.isEmpty()) {
            EmptyStateView(onAddClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.addCourseItemCollapsed()
            })
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items = courseItems, key = { _, item -> item.id }) { index, item ->
                    val isExpanded = item.id in expandedCardIds.value
                    AnimatedContent(targetState = isExpanded, transitionSpec = {
                        expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
                    }) { expanded ->
                        if (expanded) {
                            BasicInfoCardExpanded(
                                index = index + 1,
                                courseItem = item,
                                canDelete = courseItems.size > 1,
                                onUpdateName = { viewModel.updateCourseName(item.id, it) },
                                onUpdateTeacher = { viewModel.updateTeacher(item.id, it) },
                                onUpdateClassroom = { viewModel.updateDefaultClassroom(item.id, it) },
                                onDelete = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.removeCourseItem(item.id)
                                }
                            )
                        } else {
                            BasicInfoCardCollapsed(
                                index = index + 1,
                                courseItem = item,
                                onClick = { expandedCardIds.value = expandedCardIds.value + item.id }
                            )
                        }
                    }
                }

                item(key = "add_button") {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expandedCardIds.value = emptySet()
                            viewModel.addCourseItemCollapsed()
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Text(text = "添加课程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp, shadowElevation = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).systemBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.showClipboardImport(courseItems.firstOrNull()?.id ?: 0L)
                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                    Icon(Icons.Default.ContentPasteGo, null); Spacer(Modifier.width(6.dp)); Text("智能导入")
                }
                FilledTonalButton(onClick = onNext, enabled = courseItems.isNotEmpty(), modifier = Modifier.weight(2f), contentPadding = PaddingValues(vertical = 12.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null); Spacer(Modifier.width(8.dp))
                    Text(text = "下一步：详细配置", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicInfoCardExpanded(
    index: Int,
    courseItem: BatchCourseItem,
    canDelete: Boolean,
    onUpdateName: (String) -> Unit,
    onUpdateTeacher: (String) -> Unit,
    onUpdateClassroom: (String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(courseItem.courseName) { mutableStateOf(courseItem.courseName) }
    var teacher by remember(courseItem.teacher) { mutableStateOf(courseItem.teacher) }
    var classroom by remember(courseItem.defaultClassroom) { mutableStateOf(courseItem.defaultClassroom) }

    Card(modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(text = "$index", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it; onUpdateName(it) }, label = { Text("课程名称 *") }, placeholder = { Text("例如：高等数学") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp))
                if (canDelete) IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = teacher, onValueChange = { teacher = it; onUpdateTeacher(it) }, label = { Text("教师") }, placeholder = { Text("教师姓名") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp), leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)) })
                OutlinedTextField(value = classroom, onValueChange = { classroom = it; onUpdateClassroom(it) }, label = { Text("教室") }, placeholder = { Text("教室编号") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp), leadingIcon = { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)) })
            }
        }
    }
}

@Composable
private fun BasicInfoCardCollapsed(index: Int, courseItem: BatchCourseItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(text = "$index", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = courseItem.courseName.ifBlank { "未命名课程" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (courseItem.teacher.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(text = courseItem.teacher, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    if (courseItem.defaultClassroom.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(text = courseItem.defaultClassroom, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyStateView(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.AddCircleOutline,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "还没有添加课程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "点击下方按钮开始添加第一门课程",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = onAddClick,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("添加第一门课程", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
private fun DetailedConfigPhase(
    courseItems: List<BatchCourseItem>,
    currentIndex: Int,
    currentStep: Int,
    viewModel: BatchCourseCreateViewModel,
    onPreviousCourse: () -> Unit,
    onNextCourse: () -> Unit,
    onStepChange: (Int) -> Unit,
    onComplete: () -> Unit
) {
    if (courseItems.isEmpty()) return

    val currentCourse = courseItems[currentIndex]
    val haptic = LocalHapticFeedback.current
    
    // 步骤验证逻辑
    val step1Valid = currentCourse.timeSlots.isNotEmpty() &&
        currentCourse.timeSlots.all { slot ->
            slot.dayOfWeek in 1..7 &&
            slot.startSection >= 1 &&
            slot.sectionCount >= 1
        }
    
    val step2Valid = currentCourse.timeSlots.all { slot ->
        slot.customWeeks.isNotEmpty()
    }
    
    val step3Valid = true // 提醒设置无必填项
    
    val canProceed = when (currentStep) {
        1 -> step1Valid
        2 -> step2Valid
        3 -> step3Valid
        else -> false
    }
    
    val validationMessage = when (currentStep) {
        1 -> if (!step1Valid) "请完善时间段配置" else null
        2 -> if (!step2Valid) "请为所有时间段选择周次" else null
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CourseProgressIndicator(
            courses = courseItems,
            currentIndex = currentIndex,
            onCourseClick = { index -> if (index != currentIndex) {} }
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = currentCourse.courseName.takeIf { it.isNotBlank() }?.first()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentCourse.courseName.ifBlank { "未命名课程" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                if (currentCourse.teacher.isNotBlank()) append("${currentCourse.teacher} · ")
                                if (currentCourse.defaultClassroom.isNotBlank()) append(currentCourse.defaultClassroom)
                                else append("未设置教室")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ConfigStepIndicator(currentStep = currentStep, onStepClick = onStepChange)
            }
        }

        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                slideInHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { it } + fadeIn() togetherWith
                slideOutHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) { -it } + fadeOut()
            }
        ) { step ->
            when (step) {
                1 -> TimeScheduleStep(course = currentCourse, courseId = currentCourse.id, viewModel = viewModel)
                2 -> WeekSelectionStep(course = currentCourse, courseId = currentCourse.id, viewModel = viewModel)
                3 -> ReminderAndNotesStep(course = currentCourse, viewModel = viewModel)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).systemBarsPadding()
            ) {
                // 显示验证错误提示
                if (!canProceed && validationMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = validationMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStepChange(currentStep - 1)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("上一步", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (currentIndex > 0) {
                        Spacer(Modifier.weight(1f))
                    }

                    if (currentStep < 3) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStepChange(currentStep + 1)
                            },
                            enabled = canProceed,
                            modifier = Modifier.weight(2f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = when (currentStep) {
                                    1 -> "下一步：周次设置"
                                    2 -> "下一步：提醒与备注"
                                    else -> "下一步"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    } else if (currentIndex < courseItems.size - 1) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNextCourse()
                            },
                            modifier = Modifier.weight(2f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text("保存并下一门", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onComplete,
                            modifier = Modifier.weight(2f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("进入颜色选择", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseProgressIndicator(
    courses: List<BatchCourseItem>,
    currentIndex: Int,
    onCourseClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        itemsIndexed(items = courses) { index, course ->
            val isSelected = index == currentIndex
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onCourseClick(index) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                    Text(
                        text = course.courseName.ifBlank { "课程${index + 1}" }.take(6),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigStepIndicator(currentStep: Int, onStepClick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("时间", "周次", "提醒").forEachIndexed { index, label ->
            val stepNum = index + 1
            val isCompleted = currentStep > stepNum
            val isActive = currentStep == stepNum

            Surface(
                shape = CircleShape,
                color = when {
                    isCompleted -> MaterialTheme.colorScheme.primary
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                contentColor = Color.White,
                modifier = Modifier.size(24.dp),
                onClick = { if (isCompleted || isActive) onStepClick(stepNum) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCompleted) Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    else Text("$stepNum", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (index < 2) HorizontalDivider(
                modifier = Modifier.weight(1f).height(2.dp),
                color = if (currentStep > stepNum) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSelectionPhase(
    courseItems: List<BatchCourseItem>,
    viewModel: BatchCourseCreateViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Palette,
                            null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "为 ${courseItems.size} 门课程选择颜色",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "左右滑动查看更多颜色，或使用一键自动分配",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 一键分配按钮（单独占据一行，跨两列）
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "一键自动分配颜色",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "为所有课程智能分配不同颜色",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        var isAssigning by remember { mutableStateOf(false) }
                        val coroutineScope = rememberCoroutineScope()
                        
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isAssigning = true
                                viewModel.autoAssignAllColors()
                                // 延迟重置状态，确保UI更新
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(500)
                                    isAssigning = false
                                }
                            },
                            enabled = !isAssigning,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (isAssigning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isAssigning) "分配中..." else "智能分配",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            itemsIndexed(items = courseItems, key = { _, it -> it.id }) { index, course ->
                ColorSelectionCard(
                    index = index + 1,
                    courseItem = course,
                    colorOptions = BATCH_COLOR_OPTIONS,
                    onColorSelected = { colorHex ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateCourseColor(course.id, colorHex)
                    }
                )
            }

            item { Spacer(Modifier.height(100.dp)) }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).systemBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(4.dp))
                    Text("返回配置")
                }
                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNext()
                    },
                    modifier = Modifier.weight(2f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    Spacer(Modifier.width(8.dp))
                    Text("下一步：预览确认", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun ColorSelectionCard(
    index: Int,
    courseItem: BatchCourseItem,
    colorOptions: List<Color>,
    onColorSelected: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val displayColor = try {
                    Color(android.graphics.Color.parseColor(courseItem.color.ifEmpty { "#42A5F5" }))
                } catch (_: Exception) {
                    MaterialTheme.colorScheme.primary
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = displayColor,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = courseItem.courseName.ifBlank { "未命名课程" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (courseItem.teacher.isNotBlank()) courseItem.teacher else "未设置教师",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorOptions.forEach { color ->
                        val isSelected = courseItem.color == toHexString(color)
                        Surface(
                            shape = CircleShape,
                            color = color,
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                ),
                            onClick = { onColorSelected(toHexString(color)) }
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Check,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                }

                if (!scrollState.isScrollInProgress) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeScheduleStep(course: BatchCourseItem, courseId: Long, viewModel: BatchCourseCreateViewModel) {
    val haptic = LocalHapticFeedback.current
    val expandedSlotIds = remember { mutableStateOf(setOf<Long>()) }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(course.timeSlots) {
        if (course.timeSlots.isNotEmpty()) {
            val latestSlot = course.timeSlots.maxByOrNull { it.id }
            latestSlot?.let {
                expandedSlotIds.value = setOf(it.id)
                // 滚动到该时间段
                val index = course.timeSlots.indexOf(it)
                if (index >= 0) {
                    // +1 because the first item is the Card header
                    lazyListState.animateScrollToItem(index + 1)
                }
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "时间安排", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FilledTonalButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); expandedSlotIds.value = emptySet(); viewModel.addTimeSlot(courseId) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("添加时间段", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (course.timeSlots.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { Text(text = "点击上方按钮添加时间段", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        course.timeSlots.forEachIndexed { index, slot ->
                            // 反转索引，使最新的时间段显示在最上面
                            val displayIndex = course.timeSlots.size - index
                            val isExpanded = slot.id in expandedSlotIds.value
                            key(slot.id) {
                                AnimatedContent(
                                    targetState = isExpanded,
                                    transitionSpec = {
                                        expandVertically(
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            expandFrom = Alignment.Top
                                        ) togetherWith
                                        shrinkVertically(
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            shrinkTowards = Alignment.Top
                                        )
                                    },
                                    modifier = Modifier.animateItem()
                                ) { expanded ->
                                    if (expanded) TimeSlotEditRowExpanded(slotIndex = displayIndex, slot = slot, canDelete = course.timeSlots.size > 1, onDelete = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.removeTimeSlot(courseId, slot.id) }, onUpdate = { day, start, end, room -> viewModel.updateSlotDayOfWeek(courseId, slot.id, day); viewModel.updateSlotStartSection(courseId, slot.id, start); viewModel.updateSlotEndSection(courseId, slot.id, end); if (room.isNotBlank()) viewModel.updateSlotClassroom(courseId, slot.id, room) }, defaultClassroom = course.defaultClassroom, viewModel = viewModel)
                                    else TimeSlotDisplayCard(index = displayIndex, slot = slot, defaultClassroom = course.defaultClassroom, onClick = { expandedSlotIds.value = setOf(slot.id) })
                                }
                            }
                            if (index < course.timeSlots.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSlotDisplayCard(index: Int, slot: TimeSlot, defaultClassroom: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) { Box(contentAlignment = Alignment.Center) { Text(text = "$index", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)) { Text(DateUtils.getDayOfWeekName(slot.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
                    Text("${slot.startSection}-${slot.startSection + slot.sectionCount - 1}节", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (slot.classroom.isNotBlank()) Text(slot.classroom, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else if (defaultClassroom.isNotBlank()) Text(defaultClassroom, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekSelectionStep(course: BatchCourseItem, courseId: Long, viewModel: BatchCourseCreateViewModel) {
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "上课周次设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "为每个时间段单独设置上课周次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    course.timeSlots.forEachIndexed { index, slot ->
                        // 反转索引，使最新的时间段显示在最上面
                        val displayIndex = course.timeSlots.size - index
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(32.dp)) { Box(contentAlignment = Alignment.Center) { Text(text = "$displayIndex", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer) } }
                                Column(modifier = Modifier.weight(1f)) { Text(text = "${DateUtils.getDayOfWeekName(slot.dayOfWeek)} ${slot.startSection}-${slot.startSection + slot.sectionCount - 1}节", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium); Text(text = if (slot.customWeeks.isNotEmpty()) WeekParser.formatWeeksForDisplay(slot.customWeeks) else "未设置", style = MaterialTheme.typography.labelSmall, color = if (slot.customWeeks.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                                FilledTonalButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.showWeekSelector(courseId, slot.id) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text(if (slot.customWeeks.isNotEmpty()) "修改" else "设置", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        if (index < course.timeSlots.size - 1) Spacer(Modifier.height(8.dp))
                    }

                    if (course.timeSlots.isEmpty()) Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(text = "请先在\"时间安排\"中添加时间段", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun ReminderAndNotesStep(course: BatchCourseItem, viewModel: BatchCourseCreateViewModel) {
    var reminderEnabled by remember { mutableStateOf(course.reminderEnabled) }
    var reminderMinutes by remember { mutableIntStateOf(course.reminderMinutes) }
    var notes by remember { mutableStateOf(course.note) }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "🔔 课程提醒",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "课前提醒",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "在课程开始前收到通知提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                reminderEnabled = it
                                viewModel.updateReminderEnabled(course.id, it)
                            }
                        )
                    }

                    if (reminderEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Text(
                            text = "提前提醒时间",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )

                        val options = listOf(
                            5 to "5分钟",
                            10 to "10分钟",
                            15 to "15分钟",
                            30 to "30分钟",
                            60 to "1小时"
                        )
                        var showCustomInput by remember { mutableStateOf(false) }
                        var customMinutes by remember { mutableStateOf("") }
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.forEach { (minutes, label) ->
                                val isSelected = reminderMinutes == minutes && !showCustomInput
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        reminderMinutes = minutes
                                        viewModel.updateReminderMinutes(course.id, minutes)
                                        showCustomInput = false
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            
                            // 自定义选项
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (showCustomInput) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showCustomInput = true
                                    customMinutes = if (reminderMinutes !in options.map { it.first }) {
                                        reminderMinutes.toString()
                                    } else {
                                        ""
                                    }
                                }
                            ) {
                                Text(
                                    text = "自定义",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (showCustomInput) FontWeight.Bold else FontWeight.Normal,
                                    color = if (showCustomInput) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        
                        // 自定义输入框
                        if (showCustomInput) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customMinutes,
                                onValueChange = { newValue ->
                                    customMinutes = newValue
                                    newValue.toIntOrNull()?.let { minutes ->
                                        if (minutes in 1..120) {
                                            reminderMinutes = minutes
                                            viewModel.updateReminderMinutes(course.id, minutes)
                                        }
                                    }
                                },
                                label = { Text("自定义分钟数") },
                                placeholder = { Text("输入1-120之间的数字") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                isError = customMinutes.toIntOrNull()?.let { it !in 1..120 } ?: (customMinutes.isNotBlank()),
                                supportingText = {
                                    if (customMinutes.isNotBlank()) {
                                        val value = customMinutes.toIntOrNull()
                                        if (value == null) {
                                            Text("请输入有效数字", color = MaterialTheme.colorScheme.error)
                                        } else if (value !in 1..120) {
                                            Text("范围应为1-120分钟", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Text(
                            text = "📋 提醒内容预览",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                                    }
                                    Text(text = "课程提醒", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                }

                                Text(text = course.courseName.ifBlank { "未命名课程" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                                buildString {
                                    append("📍 ")
                                    val locations = course.timeSlots.mapNotNull { slot ->
                                        slot.classroom.ifBlank { course.defaultClassroom.ifBlank { null } }
                                    }.distinct()
                                    if (locations.isNotEmpty()) {
                                        append(locations.joinToString(" / "))
                                    } else {
                                        append("未设置教室")
                                    }
                                }.also {
                                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                buildString {
                                    append("⏰ ")
                                    val timeInfos = course.timeSlots.map { slot ->
                                        "${DateUtils.getDayOfWeekName(slot.dayOfWeek)} 第${slot.startSection}-${slot.startSection + slot.sectionCount - 1}节"
                                    }
                                    append(timeInfos.joinToString("、"))
                                }.also {
                                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (course.teacher.isNotBlank()) {
                                    Text(text = "👨‍🏫 ${course.teacher}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "⏰ 提前 $reminderMinutes 分钟提醒",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📝 课程备注（可选）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "添加额外信息，如考试占比、特殊要求等",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            viewModel.updateNote(course.id, it)
                        },
                        label = { Text("输入备注信息") },
                        placeholder = { Text("例如：期中考试占比30%，需带课本...") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp)
                    )

                    val templates = listOf(
                        "期中考试占比30%",
                        "需要携带绘图工具",
                        "实验课注意安全",
                        "需要自带电脑"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templates.forEach { template ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = {
                                    notes = if (notes.isBlank()) template else "$notes; $template"
                                    viewModel.updateNote(course.id, notes)
                                }
                            ) {
                                Text(
                                    text = template,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPhase(
    courseItems: List<BatchCourseItem>,
    onEdit: (Int) -> Unit,
    onSaveAll: () -> Unit,
    onBack: () -> Unit,
    viewModel: BatchCourseCreateViewModel
) {
    val haptic = LocalHapticFeedback.current
    var editingCourse by remember { mutableStateOf<BatchCourseItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "配置完成",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "共 ${courseItems.size} 门课程已准备就绪",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items = courseItems) { index, course ->
                PreviewCard(
                    index = index + 1,
                    courseItem = course,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        editingCourse = course
                    },
                    onDetailedEdit = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEdit(index)
                    }
                )
            }

            item { Spacer(Modifier.height(100.dp)) }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp).systemBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onSaveAll,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("确认完成导入", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("返回修改")
                }
            }
        }
    }
    
    // 快捷编辑对话框
    editingCourse?.let { course ->
        QuickEditDialog(
            courseItem = course,
            onDismiss = { editingCourse = null },
            onConfirm = { updatedCourse ->
                viewModel.updateCourseItem(updatedCourse)
                editingCourse = null
            }
        )
    }
}

@Composable
private fun PreviewCard(
    index: Int,
    courseItem: BatchCourseItem,
    onClick: () -> Unit,
    onDetailedEdit: () -> Unit
) {
    val defaultColor = MaterialTheme.colorScheme.primary

    val courseColor = remember(courseItem.color) {
        if (courseItem.color.isNotEmpty()) {
            try { Color(android.graphics.Color.parseColor(courseItem.color)) }
            catch (_: Exception) { defaultColor }
        } else { defaultColor }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = courseColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = courseColor
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).clickable { onClick() }) {
                    Text(
                        text = courseItem.courseName.ifBlank { "未命名课程" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (courseItem.teacher.isNotBlank() || courseItem.defaultClassroom.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (courseItem.teacher.isNotBlank()) append("${courseItem.teacher}")
                                if (courseItem.teacher.isNotBlank() && courseItem.defaultClassroom.isNotBlank()) append(" · ")
                                if (courseItem.defaultClassroom.isNotBlank()) append(courseItem.defaultClassroom)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            "快捷编辑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDetailedEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Settings,
                            "详细配置",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (courseItem.timeSlots.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = courseColor.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = courseItem.timeSlots) { slot ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = courseColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${DateUtils.getDayOfWeekName(slot.dayOfWeek)} ${slot.startSection}-${slot.startSection + slot.sectionCount - 1}节",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = courseColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "未设置时间",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSlotEditRowExpanded(
    slotIndex: Int,
    slot: TimeSlot,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onUpdate: (Int, Int, Int, String) -> Unit,
    defaultClassroom: String,
    viewModel: BatchCourseCreateViewModel
) {
    val haptic = LocalHapticFeedback.current
    val maxSectionCount by viewModel.maxSectionCount.collectAsState()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(text = "${slotIndex}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White) }
                }
                Text(text = "时间段 $slotIndex", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (canDelete) IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Close, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) }
            }

            var expandedDay by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expandedDay, onExpandedChange = { expandedDay = it }) {
                OutlinedTextField(value = DateUtils.getDayOfWeekName(slot.dayOfWeek), onValueChange = {}, readOnly = true, label = { Text("星期") }, modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true, shape = RoundedCornerShape(10.dp), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDay) })
                ExposedDropdownMenu(expanded = expandedDay, onDismissRequest = { expandedDay = false }) {
                    (1..7).forEach { day -> DropdownMenuItem(text = { Text(DateUtils.getDayOfWeekName(day)) }, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onUpdate(day, slot.startSection, slot.sectionCount, slot.classroom); expandedDay = false }) }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var startText by remember(slot.startSection) { mutableStateOf(slot.startSection.toString()) }
                var endText by remember(slot.startSection, slot.sectionCount) { mutableStateOf((slot.startSection + slot.sectionCount - 1).toString()) }
                var startError by remember { mutableStateOf<String?>(null) }
                var endError by remember { mutableStateOf<String?>(null) }
                
                OutlinedTextField(
                    value = startText,
                    onValueChange = { newValue ->
                        startText = newValue
                        newValue.toIntOrNull()?.let { newStart ->
                            if (newStart < 1 || newStart > maxSectionCount) {
                                startError = "节次范围应为1-$maxSectionCount"
                            } else {
                                startError = null
                                // 自动设置结束节次为起始+1，默认持续2节
                                val newEnd = newStart + 1
                                endText = newEnd.toString()
                                onUpdate(slot.dayOfWeek, newStart, 2, slot.classroom)
                            }
                        } ?: run {
                            startError = "请输入有效数字"
                        }
                    },
                    label = { Text("起始") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)) },
                    isError = startError != null,
                    supportingText = startError?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } }
                )
                Text(text = "~", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                OutlinedTextField(
                    value = endText,
                    onValueChange = { newValue ->
                        endText = newValue
                        newValue.toIntOrNull()?.let { newEnd ->
                            if (newEnd < 1 || newEnd > maxSectionCount) {
                                endError = "节次范围应为1-$maxSectionCount"
                            } else if (newEnd < slot.startSection) {
                                endError = "结束节次不能小于起始节次"
                            } else {
                                endError = null
                                // 计算持续节次（结束-起始+1）
                                val sectionCount = newEnd - slot.startSection + 1
                                onUpdate(slot.dayOfWeek, slot.startSection, sectionCount, slot.classroom)
                            }
                        } ?: run {
                            endError = "请输入有效数字"
                        }
                    },
                    label = { Text("结束") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = { Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp)) },
                    isError = endError != null,
                    supportingText = endError?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } }
                )
            }

            var classroom by remember(slot.classroom) { mutableStateOf(slot.classroom) }
            OutlinedTextField(value = classroom, onValueChange = { classroom = it; onUpdate(slot.dayOfWeek, slot.startSection, slot.sectionCount, it) }, label = { Text("教室（可选）") }, placeholder = { Text(if (defaultClassroom.isNotBlank()) "若是「$defaultClassroom」可留空，否则填写" else "留空或填写其他教室") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp), leadingIcon = { Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) })
        }
    }
}

private fun toHexString(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}


@Composable
private fun QuickEditDialog(
    courseItem: BatchCourseItem,
    onDismiss: () -> Unit,
    onConfirm: (BatchCourseItem) -> Unit
) {
    var name by remember { mutableStateOf(courseItem.courseName) }
    var teacher by remember { mutableStateOf(courseItem.teacher) }
    var classroom by remember { mutableStateOf(courseItem.defaultClassroom) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速编辑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = classroom,
                    onValueChange = { classroom = it },
                    label = { Text("教室") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        courseItem.copy(
                            courseName = name,
                            teacher = teacher,
                            defaultClassroom = classroom
                        )
                    )
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
