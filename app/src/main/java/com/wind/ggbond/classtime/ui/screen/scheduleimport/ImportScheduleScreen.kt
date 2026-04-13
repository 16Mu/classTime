package com.wind.ggbond.classtime.ui.screen.scheduleimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.ui.navigation.BottomNavItem
import com.wind.ggbond.classtime.ui.navigation.Screen
import com.wind.ggbond.classtime.ui.components.ScheduleSelectionState
import com.wind.ggbond.classtime.ui.components.ScheduleExpiredDialog
import com.wind.ggbond.classtime.ui.components.CreateScheduleDialog
import com.wind.ggbond.classtime.service.helper.ImportConfidence
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 导入课表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScheduleScreen(
    navController: NavController,
    viewModel: ImportScheduleViewModel = hiltViewModel()
) {
    val importState by viewModel.importState.collectAsState()
    val parsedCourses by viewModel.parsedCourses.collectAsState()
    val htmlFilePickerTrigger by viewModel.htmlFilePickerTrigger.collectAsState()
    val filePickerTrigger by viewModel.filePickerTrigger.collectAsState()
    val excelRecognitionState by viewModel.excelRecognitionState.collectAsState()
    val isCsvFile by viewModel.isCsvFile.collectAsState()
    val scheduleState by viewModel.scheduleState.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val cookieExpiredEvent by viewModel.cookieExpiredEvent.collectAsState()
    val haptic = LocalHapticFeedback.current
    
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
    
    // HTML文件选择器
    val htmlFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.handleHtmlFile(it) }
    }
    
    // 统一文件选择器（支持JSON/ICS/CSV/Excel）
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.handleFile(it) }
    }
    
    // 监听HTML文件选择器触发
    LaunchedEffect(htmlFilePickerTrigger) {
        if (htmlFilePickerTrigger) {
            htmlFilePicker.launch("text/html")
            viewModel.resetHtmlFilePickerTrigger()
        }
    }
    
    // 监听统一文件选择器触发
    LaunchedEffect(filePickerTrigger) {
        if (filePickerTrigger) {
            filePicker.launch("*/*")
            viewModel.resetFilePickerTrigger()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                // 外层 Scaffold 已处理状态栏 insets，此处置空避免双重添加
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("导入课表") },
                navigationIcon = {
                IconButton(onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navController.navigateUp()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (importState) {
                ImportState.Idle -> {
                    val showExcelWizard = excelRecognitionState !is ExcelRecognitionState.Idle
                    if (showExcelWizard) {
                        ExcelRecognitionWizard(
                            state = excelRecognitionState,
                            onConfirmRecognition = { viewModel.confirmExcelRecognition() },
                            onApplyTemplate = { viewModel.applyExcelTemplate(it) },
                            onApplyManualMapping = { mapping, headerRow -> viewModel.applyExcelManualMapping(mapping, headerRow) },
                            onDismiss = { viewModel.resetExcelRecognition() },
                            isCsvFile = isCsvFile
                        )
                    } else {
                        ImportMethodSelection(
                            onWebViewLogin = {
                                navController.navigate(Screen.SchoolSelection.route)
                            },
                            onFileImport = {
                                viewModel.selectFile()
                            },
                            onHtmlPaste = {
                                viewModel.selectHtmlFile()
                            },
                            onManualBatchCreate = {
                                navController.navigate(Screen.BatchCourseCreate.route)
                            }
                        )
                    }
                }
                
                ImportState.Loading -> {
                    ImportProgressIndicator(progress = importProgress)
                }
                
                ImportState.Preview -> {
                    ImportPreview(
                        courses = parsedCourses,
                        onConfirm = {
                            viewModel.confirmImport()
                            // 不需要手动导航，导入成功后会通过 ImportState.Success 自动导航到课程编辑界面
                        },
                        onCancel = {
                            viewModel.cancelImport()
                        }
                    )
                }
                
                is ImportState.Error -> {
                    val errorState = importState as ImportState.Error
                    ImportErrorDisplay(
                        errorState = errorState,
                        onRetry = { viewModel.retryLastAction() },
                        onReset = { viewModel.resetState() },
                        onRelogin = {
                            val schoolId = cookieExpiredEvent
                            if (schoolId != null) {
                                viewModel.onCookieExpiredHandled()
                                navController.navigate("smart_webview_import/$schoolId")
                            } else {
                                navController.navigate(Screen.SchoolSelection.route)
                            }
                        },
                        onSwitchMethod = { viewModel.resetState() }
                    )
                }
                
                ImportState.NeedScheduleSetup -> {
                    // 显示课表设置对话框
                    ScheduleSetupDialog(
                        onDismiss = {
                            viewModel.resetState()
                        },
                        onConfirm = { name, startDate, totalWeeks ->
                            viewModel.createSemesterAndImport(name, startDate, totalWeeks)
                        }
                    )
                }
                
                is ImportState.NeedSectionSetup -> {
                    val maxSection = (importState as ImportState.NeedSectionSetup).maxSection
                    SectionSetupDialog(
                        maxSection = maxSection,
                        onDismiss = {
                            viewModel.resetState()
                        },
                        onConfirm = { morning, afternoon ->
                            viewModel.updateSectionCountsAndRetry(morning, afternoon)
                        }
                    )
                }
                
                is ImportState.NeedManualFill -> {
                    val incompleteCourses = (importState as ImportState.NeedManualFill).incompleteCourses
                    ManualFillScreen(
                        incompleteCourses = incompleteCourses,
                        onFillComplete = { updatedCourses ->
                            viewModel.applyManualFilledCourses(updatedCourses)
                        },
                        onDismiss = {
                            viewModel.resetState()
                        }
                    )
                }
                
                ImportState.Success -> {
                    LaunchedEffect(Unit) {
                        // 导入成功，跳转到主界面并强制刷新数据（使用底部Tab的课表路由，确保导航栈正确清理）
                        navController.navigate(Screen.Main.createRoute(refresh = true)) {
                            popUpTo(BottomNavItem.Schedule.route) {
                                inclusive = true
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 课表设置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, java.time.LocalDate, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // 智能生成默认学期名称
    val currentDate = java.time.LocalDate.now()
    val month = currentDate.monthValue
    val year = currentDate.year
    val defaultSemesterName = if (month >= 2 && month <= 7) {
        "${year - 1}-${year}学年第二学期"
    } else {
        "${year}-${year + 1}学年第一学期"
    }
    
    // 智能生成默认开始日期（9月第一个周一或3月第一个周一）
    val defaultStartDate = if (month >= 2 && month <= 7) {
        // 春季学期，默认3月第一周的周一
        val marchFirst = java.time.LocalDate.of(year, 3, 1)
        var firstMonday = marchFirst
        while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
            firstMonday = firstMonday.plusDays(1)
        }
        firstMonday
    } else {
        // 秋季学期，默认9月第一周的周一
        val septFirst = java.time.LocalDate.of(year, 9, 1)
        var firstMonday = septFirst
        while (firstMonday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
            firstMonday = firstMonday.plusDays(1)
        }
        firstMonday
    }
    
    var semesterName by remember { mutableStateOf(defaultSemesterName) }
    var startDate by remember { mutableStateOf(defaultStartDate) }
    var totalWeeks by remember { mutableStateOf(20) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置学期信息") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 学期名称 - 提供快速选择
                var showSemesterOptions by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = showSemesterOptions,
                    onExpandedChange = { showSemesterOptions = it }
                ) {
                    OutlinedTextField(
                        value = semesterName,
                        onValueChange = { semesterName = it },
                        label = { Text("学期名称") },
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSemesterOptions) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showSemesterOptions,
                        onDismissRequest = { showSemesterOptions = false }
                    ) {
                        val currentYear = java.time.LocalDate.now().year
                        listOf(
                            "${currentYear}-${currentYear + 1}学年第一学期",
                            "${currentYear}-${currentYear + 1}学年第二学期",
                            "${currentYear - 1}-${currentYear}学年第一学期",
                            "${currentYear - 1}-${currentYear}学年第二学期"
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    semesterName = option
                                    showSemesterOptions = false
                                }
                            )
                        }
                    }
                }
                
                // 开始日期 - 使用日期选择器
                OutlinedTextField(
                    value = startDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                    onValueChange = { },
                    label = { Text("第一天上课日期") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    readOnly = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, "选择日期")
                        }
                    }
                )
                
                // 总周数 - 提供快速选择
                var showWeeksOptions by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = showWeeksOptions,
                    onExpandedChange = { showWeeksOptions = it }
                ) {
                    OutlinedTextField(
                        value = "${totalWeeks}周",
                        onValueChange = { },
                        label = { Text("学期总周数") },
                        readOnly = true,
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showWeeksOptions) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = showWeeksOptions,
                        onDismissRequest = { showWeeksOptions = false }
                    ) {
                        listOf(16, 18, 20, 22, 24).forEach { weeks ->
                            DropdownMenuItem(
                                text = { Text("${weeks}周") },
                                onClick = {
                                    totalWeeks = weeks
                                    showWeeksOptions = false
                                }
                            )
                        }
                    }
                }
                
                // 显示计算的结束日期
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "结束日期",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = startDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onConfirm(semesterName, startDate, totalWeeks)
                },
                enabled = semesterName.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }) {
                Text("取消")
            }
        }
    )
    
    // 日期选择器对话框
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 节次数设置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionSetupDialog(
    maxSection: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var morningSections by remember(maxSection) { mutableStateOf(if (maxSection <= 8) 4 else 5) }
    var afternoonSections by remember(maxSection) { mutableStateOf(maxSection - morningSections) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置课程节次数") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 提示信息
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "检测到您的课程表最多有${maxSection}节课，请设置上午和下午分别有多少节课。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                // 上午节次数（使用字符串状态管理，避免删除键光标跳动）
                var morningSectionsText by remember(morningSections) { mutableStateOf(morningSections.toString()) }
                OutlinedTextField(
                    value = morningSectionsText,
                    onValueChange = { newValue ->
                        morningSectionsText = newValue
                        newValue.toIntOrNull()?.let { morning -> 
                            if (morning in 0..maxSection) {
                                morningSections = morning
                                afternoonSections = (maxSection - morning).coerceAtLeast(0)
                            }
                        }
                    },
                    label = { Text("上午节次数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("设置为0表示没有上午课程") }
                )
                
                // 下午节次数（使用字符串状态管理，避免删除键光标跳动）
                var afternoonSectionsText by remember(afternoonSections) { mutableStateOf(afternoonSections.toString()) }
                OutlinedTextField(
                    value = afternoonSectionsText,
                    onValueChange = { newValue ->
                        afternoonSectionsText = newValue
                        newValue.toIntOrNull()?.let { afternoon -> 
                            if (afternoon in 0..maxSection) {
                                afternoonSections = afternoon
                                morningSections = (maxSection - afternoon).coerceAtLeast(0)
                            }
                        }
                    },
                    label = { Text("下午节次数") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("设置为0表示没有下午课程") }
                )
                
                // 总计
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "每天总节次数",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${morningSections + afternoonSections}节",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (morningSections + afternoonSections == maxSection) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
                
                if (morningSections + afternoonSections != maxSection) {
                    Text(
                        text = "注意：总节次数应等于${maxSection}节",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onConfirm(morningSections, afternoonSections)
                },
                enabled = morningSections + afternoonSections == maxSection
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }) {
                Text("取消")
            }
        }
    )
}

/**
 * 导入方式选择 - 推荐方式突出显示，次要方式并排，冷门方式折叠
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMethodSelection(
    onWebViewLogin: () -> Unit,
    onFileImport: () -> Unit,
    onHtmlPaste: () -> Unit,
    onManualBatchCreate: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // 控制"更多导入方式"的展开状态
    var showMoreOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "选择导入方式",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        // === 推荐方式：大CTA卡片，primaryContainer背景 ===
        Card(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onWebViewLogin()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧图标
                Icon(
                    Icons.AutoMirrored.Filled.Login,
                    contentDescription = "从教务系统导入",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                // 中间文字
                Column(modifier = Modifier.weight(1f)) {
                    // "推荐"标签
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            "推荐",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // 标题
                    Text(
                        "从教务系统一键导入",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    // 描述
                    Text(
                        "支持27+所高校，登录后自动获取课表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                // 右侧箭头
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }

        // === 次要方式：两个小卡片并排 ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 手动添加课程
            Card(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onManualBatchCreate()
                },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = "手动添加",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "手动添加",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "逐条填写课程信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 从文件导入（支持JSON/CSV/Excel）
            // TODO: [临时禁用Excel/CSV导入功能 - 后续需要恢复]
            // 原描述: "JSON / CSV / xlsx"
            Card(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFileImport()
                },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = "从文件导入",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "从文件导入",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "JSON / ICS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // === 更多导入方式：折叠区域 ===
        Surface(
            onClick = { showMoreOptions = !showMoreOptions },
            modifier = Modifier.fillMaxWidth(),
            color = androidx.compose.ui.graphics.Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "更多导入方式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    if (showMoreOptions) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (showMoreOptions) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 折叠内容：导入网页课表文件（不使用HTML术语）
        androidx.compose.animation.AnimatedVisibility(visible = showMoreOptions) {
            Card(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onHtmlPaste()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = "导入网页课表",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "导入网页课表文件",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "从浏览器保存课表页面后导入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ImportPreview(
    courses: List<ParsedCourse>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "导入预览",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "共 ${courses.size} 门课程",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(courses) { course ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = course.courseName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (course.teacher.isNotEmpty()) {
                            Text("教师：${course.teacher}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (course.classroom.isNotEmpty()) {
                            Text("教室：${course.classroom}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("时间：周${course.dayOfWeek} 第${course.startSection}节", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCancel()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onConfirm()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("确认导入")
            }
        }
    }
}

@Composable
fun ErrorDisplay(
    message: String,
    onRetry: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "导入失败",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRetry()
        }) {
            Text("重试")
        }
    }
}

@Composable
fun ImportErrorDisplay(
    errorState: ImportState.Error,
    onRetry: () -> Unit,
    onReset: () -> Unit,
    onRelogin: () -> Unit,
    onSwitchMethod: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val errorIcon = when (errorState.errorType) {
        ImportErrorType.NETWORK -> Icons.Default.WifiOff
        ImportErrorType.PARSE -> Icons.Default.BrokenImage
        ImportErrorType.COOKIE_EXPIRED -> Icons.Default.Lock
        ImportErrorType.UNKNOWN -> Icons.Default.Error
    }
    val errorTitle = when (errorState.errorType) {
        ImportErrorType.NETWORK -> "网络连接失败"
        ImportErrorType.PARSE -> "数据解析失败"
        ImportErrorType.COOKIE_EXPIRED -> "登录已过期"
        ImportErrorType.UNKNOWN -> "导入失败"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            errorIcon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = errorTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorState.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (errorState.retryAction) {
                ImportErrorRetryAction.RETRY -> {
                    OutlinedButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReset()
                    }) {
                        Text("取消")
                    }
                    Button(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRetry()
                    }) {
                        Text("重试")
                    }
                }
                ImportErrorRetryAction.RELOGIN -> {
                    OutlinedButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReset()
                    }) {
                        Text("取消")
                    }
                    Button(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRelogin()
                    }) {
                        Text("重新登录")
                    }
                }
                ImportErrorRetryAction.SWITCH_METHOD -> {
                    OutlinedButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReset()
                    }) {
                        Text("取消")
                    }
                    Button(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSwitchMethod()
                    }) {
                        Text("切换导入方式")
                    }
                }
                ImportErrorRetryAction.RESET -> {
                    Button(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReset()
                    }) {
                        Text("返回")
                    }
                }
            }
        }
    }
}

@Composable
fun ImportProgressIndicator(progress: ImportProgress) {
    val steps = ImportStep.entries
    val currentStepIndex = steps.indexOf(progress.step)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = progress.step.label + "...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(32.dp))
        steps.forEachIndexed { index, step ->
            val isActive = index == currentStepIndex
            val isCompleted = index < currentStepIndex
            val isPending = index > currentStepIndex

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = 4.dp)
            ) {
                when {
                    isCompleted -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    isActive -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    isPending -> Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    object Preview : ImportState()
    object NeedScheduleSetup : ImportState()
    data class NeedSectionSetup(val maxSection: Int) : ImportState()
    data class NeedManualFill(
        val incompleteCourses: List<IncompleteCourseInfo>
    ) : ImportState()
    object Success : ImportState()
    data class Error(
        val message: String,
        val errorType: ImportErrorType = ImportErrorType.UNKNOWN,
        val retryAction: ImportErrorRetryAction = ImportErrorRetryAction.RESET
    ) : ImportState()
}

data class IncompleteCourseInfo(
    val index: Int,
    val course: ParsedCourse,
    val missingFields: List<String>
)

enum class ImportErrorType {
    NETWORK,
    PARSE,
    COOKIE_EXPIRED,
    UNKNOWN
}

enum class ImportErrorRetryAction {
    RESET,
    RETRY,
    RELOGIN,
    SWITCH_METHOD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelRecognitionWizard(
    state: ExcelRecognitionState,
    onConfirmRecognition: () -> Unit,
    onApplyTemplate: (String) -> Unit,
    onApplyManualMapping: (Map<String, Int>, Int) -> Unit,
    onDismiss: () -> Unit,
    isCsvFile: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val fileTypeLabel = if (isCsvFile) "CSV" else "Excel"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                text = "${fileTypeLabel}智能识别",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        when (state) {
            is ExcelRecognitionState.Analyzing -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在智能识别${fileTypeLabel}文件...")
                    }
                }
            }

            is ExcelRecognitionState.NeedConfirmation -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "识别结果需要确认",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "检测到${state.courses.size}门课程，模板: ${state.decision.templateName ?: "通用格式"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (state.decision.suggestedAction.isNotEmpty()) {
                            Text(
                                state.decision.suggestedAction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onConfirmRecognition()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确认识别结果")
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新选择")
                }
            }

            is ExcelRecognitionState.NeedManualSetup -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "无法自动识别",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "请选择教务系统模板或手动设置字段映射",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Text(
                    "选择教务系统模板",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                listOf(
                    "ZHENGFANG" to "正方教务",
                    "QINGGUO" to "青果教务",
                    "URP" to "URP教务"
                ).forEach { (templateName, displayName) ->
                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onApplyTemplate(templateName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.preview.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "文件预览（前${minOf(state.preview.size, 5)}行）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.preview.take(5).forEachIndexed { rowIdx, row ->
                        Text(
                            row.take(6).joinToString(" | "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新选择文件")
                }
            }

            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualFillScreen(
    incompleteCourses: List<IncompleteCourseInfo>,
    onFillComplete: (List<IncompleteCourseInfo>) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val filledData = remember(incompleteCourses) {
        mutableStateMapOf<Int, MutableMap<String, String>>().apply {
            incompleteCourses.forEach { info ->
                this[info.index] = mutableMapOf(
                    "weeks" to info.course.weekExpression,
                    "dayOfWeek" to if (info.course.dayOfWeek in 1..7) info.course.dayOfWeek.toString() else "",
                    "startSection" to if (info.course.startSection > 0) info.course.startSection.toString() else "",
                    "sectionCount" to if (info.course.sectionCount > 0) info.course.sectionCount.toString() else "1"
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                text = "补充课程信息",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "以下课程的部分信息未能自动识别，请手动补充。标有 * 的为必填项。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(incompleteCourses) { info ->
                val courseData = filledData[info.index] ?: mutableMapOf()
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = info.course.courseName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (info.course.teacher.isNotEmpty()) {
                            Text(
                                "教师：${info.course.teacher}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (info.course.classroom.isNotEmpty()) {
                            Text(
                                "教室：${info.course.classroom}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val missingLabel = info.missingFields.joinToString("、") {
                            when (it) {
                                "weeks" -> "上课周次"
                                "dayOfWeek" -> "星期"
                                "startSection" -> "开始节次"
                                "sectionCount" -> "持续节数"
                                else -> it
                            }
                        }
                        Text(
                            text = "缺失：$missingLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if ("weeks" in info.missingFields) {
                            var weeksText by remember(courseData) {
                                mutableStateOf(courseData["weeks"] ?: "")
                            }
                            OutlinedTextField(
                                value = weeksText,
                                onValueChange = { newValue ->
                                    weeksText = newValue
                                    courseData["weeks"] = newValue
                                },
                                label = { Text("上课周次 *") },
                                placeholder = { Text("如：1-16周 或 1,3,5,7,9周") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                supportingText = { Text("支持格式：1-16周、1-16单周、1,3,5,7") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if ("dayOfWeek" in info.missingFields) {
                            var dayText by remember(courseData) {
                                mutableStateOf(courseData["dayOfWeek"] ?: "")
                            }
                            var dayExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = dayExpanded,
                                onExpandedChange = { dayExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = when (dayText) {
                                        "1" -> "周一"; "2" -> "周二"; "3" -> "周三"
                                        "4" -> "周四"; "5" -> "周五"; "6" -> "周六"; "7" -> "周日"
                                        else -> dayText
                                    },
                                    onValueChange = {},
                                    label = { Text("星期 *") },
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = dayExpanded,
                                    onDismissRequest = { dayExpanded = false }
                                ) {
                                    listOf("周一" to "1", "周二" to "2", "周三" to "3",
                                        "周四" to "4", "周五" to "5", "周六" to "6", "周日" to "7"
                                    ).forEach { (label, value) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                dayText = value
                                                courseData["dayOfWeek"] = value
                                                dayExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if ("startSection" in info.missingFields) {
                            var sectionText by remember(courseData) {
                                mutableStateOf(courseData["startSection"] ?: "")
                            }
                            OutlinedTextField(
                                value = sectionText,
                                onValueChange = { newValue ->
                                    sectionText = newValue
                                    courseData["startSection"] = newValue
                                },
                                label = { Text("开始节次 *") },
                                placeholder = { Text("如：1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if ("sectionCount" in info.missingFields) {
                            var countText by remember(courseData) {
                                mutableStateOf(courseData["sectionCount"] ?: "1")
                            }
                            OutlinedTextField(
                                value = countText,
                                onValueChange = { newValue ->
                                    countText = newValue
                                    courseData["sectionCount"] = newValue
                                },
                                label = { Text("持续节数 *") },
                                placeholder = { Text("如：2") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("取消")
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val updatedCourses = incompleteCourses.map { info ->
                        val data = filledData[info.index] ?: emptyMap()
                        val original = info.course
                        info.copy(
                            course = original.copy(
                                weekExpression = data["weeks"]?.takeIf { it.isNotBlank() } ?: original.weekExpression,
                                dayOfWeek = data["dayOfWeek"]?.toIntOrNull() ?: original.dayOfWeek,
                                startSection = data["startSection"]?.toIntOrNull() ?: original.startSection,
                                sectionCount = data["sectionCount"]?.toIntOrNull() ?: original.sectionCount
                            )
                        )
                    }
                    onFillComplete(updatedCourses)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("确认并继续")
            }
        }
    }
}


